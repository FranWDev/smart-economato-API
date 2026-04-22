import { ZodError } from 'zod';
import { GetCostBreakdownTool } from '../get-cost-breakdown.tool';
import { InventoryClient } from '../../../mcp/inventory.client';
import { ANALYSIS_BASE } from '../../constants';

describe('GetCostBreakdownTool', () => {
  let tool: GetCostBreakdownTool;
  let mockClient: Partial<InventoryClient>;

  beforeEach(() => {
    tool = new GetCostBreakdownTool();
    mockClient = {
      get: jest.fn().mockResolvedValue({ totalCost: 1500, breakdown: [] }),
    };
  });

  describe('schema validation', () => {
    it('should accept valid date range', () => {
      const result = tool.schema.parse({
        from: '2025-01-01',
        to: '2025-01-31',
      });
      expect(result).toEqual({ from: '2025-01-01', to: '2025-01-31' });
    });

    it('should reject invalid date format', () => {
      expect(() =>
        tool.schema.parse({ from: '01-01-2025', to: '2025-01-31' }),
      ).toThrow(ZodError);
    });

    it('should reject missing fields', () => {
      expect(() => tool.schema.parse({ from: '2025-01-01' })).toThrow(ZodError);
      expect(() => tool.schema.parse({})).toThrow(ZodError);
    });
  });

  describe('execute', () => {
    it('should call client.get with from and to as query params', async () => {
      await tool.execute(
        { from: '2025-01-01', to: '2025-01-31' },
        mockClient as InventoryClient,
        'jwt',
      );

      expect(mockClient.get).toHaveBeenCalledWith(
        `${ANALYSIS_BASE}/cost-breakdown`,
        'jwt',
        { from: '2025-01-01', to: '2025-01-31' },
      );
    });
  });
});
